import { PassengerHeader } from "../../../components/passenger-header";
import { TrainDetailsExperience } from "../../../components/train-details-experience";

export const metadata = {
  title: "Train route · Laptop",
};

export default function LaptopTrainDetailsPage() {
  return (
    <div className="page-shell">
      <PassengerHeader experience="laptop" />
      <TrainDetailsExperience device="laptop" />
      <footer className="site-footer laptop-footer">
        <span>RailConnect — independent passenger timetable search</span>
      </footer>
    </div>
  );
}
