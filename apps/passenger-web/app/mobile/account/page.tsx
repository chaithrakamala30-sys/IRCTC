import { PassengerAccountExperience } from "../../../components/identity-experience";

export const metadata = { title: "Account — Mobile passenger" };

export default function MobileAccountPage() {
  return <PassengerAccountExperience device="mobile" />;
}
